import { BrowserRouter, Route, Routes } from 'react-router-dom'
import { PhoneFrame } from './components/PhoneFrame'
import { RequireAuth } from './components/RequireAuth'
import { paths } from './routes'
import { AuthProvider } from './state/auth'
import { Home } from './screens/Home'
import { MyPage } from './screens/MyPage'
import { FriendQr } from './screens/FriendQr'
import { Reunion } from './screens/Reunion'
import { Camera } from './screens/Camera'
import { AlbumCreate } from './screens/AlbumCreate'
import { AlbumGenerating } from './screens/AlbumGenerating'
import { Decorate } from './screens/Decorate'
import { Share } from './screens/Share'
import { CapsuleCreate } from './screens/CapsuleCreate'
import { CapsuleDone } from './screens/CapsuleDone'
import { Detail } from './screens/Detail'

function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <PhoneFrame>
          <Routes>
            <Route path={paths.home} element={<Home />} />
            <Route element={<RequireAuth />}>
              <Route path={paths.me} element={<MyPage />} />
              <Route path={paths.friendQr} element={<FriendQr />} />
              <Route path={paths.reunion} element={<Reunion />} />
              <Route path={paths.camera} element={<Camera />} />
              <Route path={paths.albumCreate} element={<AlbumCreate />} />
              <Route path={paths.albumGenerating} element={<AlbumGenerating />} />
              <Route path={paths.decorate} element={<Decorate />} />
              <Route path={paths.share} element={<Share />} />
              <Route path={paths.capsuleCreate} element={<CapsuleCreate />} />
              <Route path={paths.capsuleDone} element={<CapsuleDone />} />
              <Route path={paths.detail} element={<Detail />} />
            </Route>
          </Routes>
        </PhoneFrame>
      </AuthProvider>
    </BrowserRouter>
  )
}

export default App
