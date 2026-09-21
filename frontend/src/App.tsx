import { BrowserRouter, Route, Routes } from 'react-router-dom'
import { PhoneFrame } from './components/PhoneFrame'
import { routes } from './routes'
import { Home } from './screens/Home'
import { Camera } from './screens/Camera'
import { AlbumCreate } from './screens/AlbumCreate'
import { Decorate } from './screens/Decorate'
import { Share } from './screens/Share'
import { CapsuleCreate } from './screens/CapsuleCreate'
import { CapsuleDone } from './screens/CapsuleDone'
import { Detail } from './screens/Detail'

function App() {
  return (
    <BrowserRouter>
      <PhoneFrame>
        <Routes>
          <Route path={routes.home} element={<Home />} />
          <Route path={routes.camera} element={<Camera />} />
          <Route path={routes.albumCreate} element={<AlbumCreate />} />
          <Route path={routes.decorate} element={<Decorate />} />
          <Route path={routes.share} element={<Share />} />
          <Route path={routes.capsuleCreate} element={<CapsuleCreate />} />
          <Route path={routes.capsuleDone} element={<CapsuleDone />} />
          <Route path={routes.detail} element={<Detail />} />
        </Routes>
      </PhoneFrame>
    </BrowserRouter>
  )
}

export default App
