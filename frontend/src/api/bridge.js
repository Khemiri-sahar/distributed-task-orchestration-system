import axios from 'axios'

const BASE = 'http://localhost:8000/api'

export async function fetchStatus() {
  try {
    const res = await axios.get(`${BASE}/status`)
    return res.data
  } catch (e) {
    throw new Error(e.response?.data?.detail || e.message)
  }
}

export async function submitTask(taskType, payload) {
  try {
    const res = await axios.post(`${BASE}/task`, { taskType, payload })
    return res.data
  } catch (e) {
    throw new Error(e.response?.data?.detail || e.message)
  }
}

export async function fetchLogs() {
  try {
    const res = await axios.get(`${BASE}/logs`)
    return res.data
  } catch (e) {
    throw new Error(e.response?.data?.detail || e.message)
  }
}

export async function killNode(nodeId) {
  try {
    const res = await axios.post(`${BASE}/kill/${nodeId}`)
    return res.data
  } catch (e) {
    throw new Error(e.response?.data?.detail || e.message)
  }
}

export async function reviveNode(nodeId) {
  try {
    const res = await axios.post(`${BASE}/revive/${nodeId}`)
    return res.data
  } catch (e) {
    throw new Error(e.response?.data?.detail || e.message)
  }
}
