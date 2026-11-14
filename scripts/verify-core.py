"""Integration checks for a disposable LinkStamp MySQL schema and Redis instance."""
import argparse
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timedelta, timezone
import json
import re
import secrets
import subprocess
import time
import urllib.error
import urllib.parse
import urllib.request


TARGET = "https://example.com/"
USER_AGENT = "Mozilla/5.0 Chrome/130.0.0.0 Safari/537.36"
STATS_ZONE = timezone(timedelta(hours=8))
STREAM = "short-link:stats-stream"
STREAM_GROUP = "short-link:stats-stream:only-group"
DEAD_LETTER = "short-link:stats-stream:dead-letter"


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def run(options):
    suffix = secrets.token_hex(3)
    base = f"http://127.0.0.1:{options.gateway_port}/api/short-link/admin/v1"
    short_domain = f"127.0.0.1:{options.short_port}"
    opener = urllib.request.build_opener(NoRedirect())

    def command(args, stdin=None):
        return subprocess.check_output(args, input=stdin, encoding="utf-8", timeout=20).strip()

    def sql(statement):
        return command([options.mysql, "--no-defaults", "-h", "127.0.0.1",
                        "-P", str(options.mysql_port), "-u", "root", "-N", "-B",
                        "--delimiter=//", options.database, "-e", statement])

    def redis(*args, stdin=None):
        cli = [options.redis_cli, "--raw", "-h", "127.0.0.1", "-p", str(options.redis_port)]
        if stdin is not None:
            cli.append("-x")
        return command([*cli, *args], stdin)

    def add_event(payload):
        # The Windows Cygwin client can expand a bare "*" command-line argument.
        record_id = redis("EVAL", "return redis.call('XADD',KEYS[1],'*','statsRecord',ARGV[1])",
                          "1", STREAM, stdin=payload)
        assert re.fullmatch(r"[0-9]+-[0-9]+", record_id), record_id
        return record_id

    def request(path, payload=None, headers=None, raw=False):
        data = None if payload is None else json.dumps(payload).encode()
        headers = {"User-Agent": USER_AGENT, **(headers or {})}
        if data is not None:
            headers["Content-Type"] = "application/json"
        with opener.open(urllib.request.Request(base + path, data, headers), timeout=25) as response:
            return response.read() if raw else json.load(response)

    def success(result):
        assert result.get("code") == "0", result
        return result.get("data")

    def account(prefix):
        username = prefix + suffix
        password = "Example!" + secrets.token_hex(6)
        success(request("/user", {"username": username, "password": password,
                                  "realName": "Regression", "phone": prefix + str(int(suffix, 16)).zfill(8),
                                  "mail": username + "@example.com"}))
        session = success(request("/user/login", {"username": username, "password": password}))
        headers = {"username": username, "token": session["token"]}
        groups = success(request("/group", headers=headers))
        assert len(groups) == 1
        return headers, groups[0]["gid"]

    def create(user, gid, code=None):
        form = {"originUrl": TARGET, "gid": gid, "createdType": 1, "validDateType": 0, "describe": "Regression"}
        if code:
            form["customCode"] = code
        return request("/create", form, user)

    def batch(user, gid, descriptions):
        return request("/create/batch", {"originUrls": [TARGET] * len(descriptions),
                       "describes": descriptions, "gid": gid, "createdType": 1,
                       "validDateType": 0}, user, raw=True)

    def visit(url, target=TARGET, cookie=None):
        headers = {"User-Agent": USER_AGENT}
        if cookie:
            headers["Cookie"] = "uv=" + cookie
        try:
            opener.open(urllib.request.Request(url, headers=headers), timeout=15)
        except urllib.error.HTTPError as result:
            assert result.code == 302, result.code
            assert urllib.parse.urljoin(url, result.headers["Location"]) == target, result.headers["Location"]
        else:
            raise AssertionError("Expected HTTP 302")

    def count_shards(table, condition):
        values = [f"(SELECT COUNT(*) FROM {table}_{number} WHERE {condition})" for number in range(16)]
        return int(sql("SELECT " + " + ".join(values)))

    def eventually(action, description, seconds=28):
        deadline = time.monotonic() + seconds
        while time.monotonic() < deadline:
            result = action()
            if result:
                return result
            time.sleep(0.25)
        raise AssertionError(description)

    def page_row(user, gid, url):
        query = urllib.parse.urlencode({"gid": gid, "current": 1, "size": 50})
        page = success(request("/page?" + query, headers=user))
        return next(row for row in page["records"] if row["fullShortUrl"] == url)

    def enqueue(full_url, timestamp, visitor="regression-visitor", ip="192.0.2.9"):
        payload = json.dumps({"fullShortUrl": full_url, "uv": visitor, "remoteAddr": ip,
                              "os": "Windows", "browser": "Chrome", "device": "PC",
                              "network": "WIFI", "currentDate": int(timestamp.timestamp() * 1000)})
        return add_event(payload)

    def acknowledged(record_id):
        return not redis("XRANGE", STREAM, record_id, record_id)

    first, gid = account("138")
    second, other_gid = account("139")
    assert gid.isalnum() and other_gid.isalnum()
    assert create(second, gid, "c" + suffix)["code"] != "0"
    for invalid in ("api", "page", "actuator", "error", "A12", "ab"):
        assert create(first, gid, invalid)["code"] != "0"
    print("PASS registration, login, group ownership and reserved codes", flush=True)
    time.sleep(1.1)

    code = "c" + suffix
    with ThreadPoolExecutor(max_workers=2) as pool:
        replies = list(pool.map(lambda user: create(*user, code), [(first, gid), (second, other_gid)]))
    assert sorted(reply["code"] == "0" for reply in replies) == [False, True], replies
    full_url = f"{short_domain}/{code}"
    url = "http://" + full_url
    assert count_shards("t_link", f"full_short_url = '{full_url}'") == 1
    assert count_shards("t_link_goto", f"full_short_url = '{full_url}'") == 1
    visit(url, cookie="race-visitor")
    owner, owner_gid = (first, gid) if replies[0]["code"] == "0" else (second, other_gid)
    time.sleep(1.1)
    auto = success(create(first, gid))["fullShortUrl"]
    visit(auto)
    print("PASS concurrent custom code, exact row counts and both HTTP 302 redirects", flush=True)

    blocked = "rollback-" + suffix
    valid_description = "success-" + suffix
    failed_item = " OR ".join(
        f"EXISTS(SELECT 1 FROM t_link_{number} WHERE full_short_url=NEW.full_short_url AND `describe`='{blocked}')"
        for number in range(16)
    )
    created_triggers = []
    try:
        for number in range(16):
            name = f"regression_route_{number}"
            sql(f"CREATE TRIGGER {name} BEFORE INSERT ON t_link_goto_{number} FOR EACH ROW "
                f"BEGIN IF NEW.gid = '{gid}' AND ({failed_item}) THEN "
                "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'regression route write'; END IF; END")
            created_triggers.append(name)
        assert batch(first, gid, [blocked, valid_description]).startswith(b"PK")
        assert count_shards("t_link", f"`describe` = '{blocked}'") == 0
        assert count_shards("t_link", f"`describe` = '{valid_description}'") == 1
    finally:
        for trigger in created_triggers:
            sql(f"DROP TRIGGER {trigger}")
    print("PASS failed second write rolls back the batch item; successful item exports XLSX", flush=True)

    time.sleep(1.1)
    stats_url = success(create(first, gid, "s" + suffix))["fullShortUrl"]
    stats_full_url = urllib.parse.urlsplit(stats_url).netloc + urllib.parse.urlsplit(stats_url).path
    previous = datetime.now(STATS_ZONE).replace(hour=23, minute=58, second=0, microsecond=0) - timedelta(days=1)
    today = previous + timedelta(minutes=3)
    ids = [enqueue(stats_full_url, previous), enqueue(stats_full_url, today),
           enqueue(stats_full_url, today + timedelta(seconds=2))]
    eventually(lambda: all(acknowledged(record) for record in ids), "Cross-day visits not consumed")
    actual = sql("SELECT today_pv,today_uv,today_uip FROM t_link_stats_today "
                 f"WHERE full_short_url='{stats_full_url}' ORDER BY date")
    assert actual.splitlines() == ["1\t1\t1", "2\t1\t1"], actual
    totals = page_row(first, gid, stats_full_url)
    assert [totals[field] for field in ("totalPv","totalUv","totalUip")] == [3, 1, 1], totals
    assert [totals[field] for field in ("todayPv","todayUv","todayUip")] == [2, 1, 1], totals
    query = urllib.parse.urlencode({"gid": gid, "fullShortUrl": stats_full_url, "enableStatus": 0,
                                    "startDate": f"{previous.date()} 00:00:00", "endDate": f"{today.date()} 23:59:59"})
    stats = success(request("/stats?" + query, headers=first))
    assert [stats[field] for field in ("pv", "uv", "uip")] == [3, 1, 1], stats
    assert [[day[field] for field in ("pv", "uv", "uip")] for day in stats["daily"]] == [[1,1,1],[2,1,1]], stats
    print("PASS UTC+8 cross-day daily, range and cumulative PV/UV/IP", flush=True)

    sql("CREATE TRIGGER regression_stats_retry BEFORE INSERT ON t_link_stats_today FOR EACH ROW "
        f"BEGIN IF NEW.full_short_url = '{stats_full_url}' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'regression stats write'; END IF; END")
    try:
        record = enqueue(stats_full_url, today + timedelta(seconds=3), "retry-visitor", "192.0.2.10")
        eventually(lambda: record in redis("XPENDING", STREAM, STREAM_GROUP, "-", "+", "100"), "Failed event did not remain pending")
        time.sleep(0.5)
        assert sql(f"SELECT COUNT(*) FROM t_link_stats_event WHERE stream_id='{record}'") == "0"
        assert sql(f"SELECT COUNT(*) FROM t_link_access_logs WHERE full_short_url='{stats_full_url}' AND user='retry-visitor'") == "0"
    finally:
        sql("DROP TRIGGER regression_stats_retry")
    eventually(lambda: acknowledged(record), "Visit did not recover from Pending")
    assert sql(f"SELECT COUNT(*) FROM t_link_stats_event WHERE stream_id='{record}'") == "1"
    assert sql(f"SELECT COUNT(*) FROM t_link_access_logs WHERE full_short_url='{stats_full_url}' AND user='retry-visitor'") == "1"
    # Reserve the event ID in MySQL before redelivery to simulate a commit followed by a lost ACK.
    raw_seconds, raw_microseconds = map(int, redis("TIME").splitlines())
    latest = redis("XREVRANGE", STREAM, "+", "-", "COUNT", "1").splitlines()
    last_millis = int(latest[0].split("-")[0]) if latest else 0
    replay = f"{max(raw_seconds * 1000 + raw_microseconds // 1000, last_millis) + 100}-0"
    sql(f"INSERT INTO t_link_stats_event(stream_id) VALUES ('{replay}')")
    duplicate_payload = json.dumps({"fullShortUrl": stats_full_url, "uv": "duplicate-must-not-write",
                                    "remoteAddr": "192.0.2.12", "currentDate": int(today.timestamp() * 1000)})
    assert redis("XADD", STREAM, replay, "statsRecord", stdin=duplicate_payload) == replay
    eventually(lambda: acknowledged(replay), "Already committed event was not acknowledged")
    assert sql(f"SELECT COUNT(*) FROM t_link_access_logs WHERE full_short_url='{stats_full_url}' AND user='duplicate-must-not-write'") == "0"
    failed_forever = enqueue(f"{short_domain}/missing-{suffix}", today)
    eventually(lambda: acknowledged(failed_forever) and failed_forever in redis("XRANGE", DEAD_LETTER, "-", "+"),
               "Exhausted retries did not reach dead letter storage")
    assert sql(f"SELECT COUNT(*) FROM t_link_stats_event WHERE stream_id='{failed_forever}'") == "0"
    poison = add_event("{broken json")
    eventually(lambda: acknowledged(poison) and poison in redis("XRANGE", DEAD_LETTER, "-", "+"), "Poison event was not archived")
    print("PASS stats rollback, Pending recovery, duplicate protection and poison-message archive", flush=True)

    record = {"gid": owner_gid, "fullShortUrl": full_url}
    success(request("/recycle-bin/save", record, owner))
    visit(url, f"http://{short_domain}/page/notfound")
    success(request("/recycle-bin/recover", record, owner))
    visit(url)
    success(request("/recycle-bin/save", record, owner))
    success(request("/recycle-bin/remove", record, owner))
    assert create(first, gid, code)["code"] != "0"
    print("PASS recycle, restore and retired code stays reserved", flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--database", required=True)
    parser.add_argument("--mysql", default="mysql")
    parser.add_argument("--mysql-port", type=int, default=23307)
    parser.add_argument("--redis-cli", default="redis-cli")
    parser.add_argument("--redis-port", type=int, default=26380)
    parser.add_argument("--gateway-port", type=int, default=28000)
    parser.add_argument("--short-port", type=int, default=28003)
    args = parser.parse_args()
    if not args.database.endswith("_regression"):
        parser.error("Use a disposable database whose name ends with _regression.")
    run(args)
