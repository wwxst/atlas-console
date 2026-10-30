import { useEffect } from 'react'
import { BrowserRouter } from 'react-router-dom'
import AppRouter from './router'
import { useAppStore } from './stores/appStore'
import { RequestErrorToast } from '@ui/index'

function App() {
  const theme = useAppStore((state) => state.theme)

  useEffect(() => { document.documentElement.dataset.theme = theme }, [theme])

  return <BrowserRouter><AppRouter /><RequestErrorToast /></BrowserRouter>
}

export default App
