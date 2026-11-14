// LinkStamp changes: retain the date utilities used by the statistics views.
import dayjs from 'dayjs'

export function getTodayFormatDate() {
  return dayjs().format('YYYY-MM-DD') + ' 23:59:59'
}

export function getLastWeekFormatDate() {
  return dayjs().subtract(6, 'day').format('YYYY-MM-DD') + ' 00:00:00'
}
