import { app, BrowserWindow, session, shell } from 'electron'
import path from 'node:path'

const devServerUrl = process.env.VITE_DEV_SERVER_URL
let mainWindow: BrowserWindow | null = null

function createWindow(): void {
  mainWindow = new BrowserWindow({
    width: 1440,
    height: 920,
    minWidth: 1024,
    minHeight: 680,
    show: false,
    autoHideMenuBar: true,
    backgroundColor: '#0a0a0a',
    icon: path.join(__dirname, '../dist/logo.png'),
    webPreferences: {
      preload: path.join(__dirname, 'preload.cjs'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: false,
    },
  })

  mainWindow.once('ready-to-show', () => {
    mainWindow?.show()
  })

  if (devServerUrl) {
    void mainWindow.loadURL(devServerUrl)
  } else {
    void mainWindow.loadFile(path.join(__dirname, '../dist/index.html'))
  }

  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    void shell.openExternal(url)
    return { action: 'deny' }
  })

  mainWindow.webContents.on('will-navigate', (event, url) => {
    const allowed = devServerUrl ? url.startsWith(devServerUrl) : url.startsWith('file://')
    if (!allowed) {
      event.preventDefault()
      void shell.openExternal(url)
    }
  })

  mainWindow.on('closed', () => {
    mainWindow = null
  })
}

app.setAppUserModelId('com.cognia.desktop')

app.whenReady().then(() => {
  // The packaged renderer is loaded from file://, whose Origin is "null".
  // The API uses bearer tokens (not cookies), so allowing the local API's
  // responses is sufficient for CORS without weakening the web deployment.
  session.defaultSession.webRequest.onHeadersReceived(
    { urls: ['http://localhost:8080/*', 'http://127.0.0.1:8080/*'] },
    (details, callback) => {
      const headers = { ...details.responseHeaders }
      headers['Access-Control-Allow-Origin'] = ['*']
      headers['Access-Control-Allow-Headers'] = ['*']
      headers['Access-Control-Allow-Methods'] = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'OPTIONS']
      callback({ responseHeaders: headers })
    },
  )

  createWindow()
  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) {
      createWindow()
    }
  })
})

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') {
    app.quit()
  }
})
