// LinkStamp changes: centralize saving and clearing the browser session.
import Cookies from 'js-cookie'

export function getToken() {
  return Cookies.get('token')
}

export function getUsername() {
  return Cookies.get('username')
}

export function saveSession(username, token) {
  const options = { sameSite: 'Lax', secure: window.location.protocol === 'https:' }
  Cookies.set('token', token, options)
  Cookies.set('username', username, options)
  localStorage.setItem('token', token)
  localStorage.setItem('username', username)
}

export function restoreSession() {
  const isSet = (value) => Boolean(value && value !== 'null' && value !== 'undefined')
  if (isSet(getToken()) && isSet(getUsername())) return true
  const token = localStorage.getItem('token')
  const username = localStorage.getItem('username')
  if (!isSet(token) || !isSet(username)) return false
  saveSession(username, token)
  return true
}

export function clearSession() {
  Cookies.remove('token')
  Cookies.remove('username')
  localStorage.removeItem('token')
  localStorage.removeItem('username')
}
