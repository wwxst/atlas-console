import { BrowserRouter } from 'react-router-dom'
import AppLayout from './layouts/AppLayout'
import AppRouter from './router'

function App() {
  return <BrowserRouter><AppLayout><AppRouter /></AppLayout></BrowserRouter>
}

export default App
