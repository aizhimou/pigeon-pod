import { useContext, useEffect } from 'react';
import { Route, Routes } from 'react-router-dom';
import { UserContext } from './context/User/UserContext.jsx';
import { PlayerProvider } from './context/PlayerContext.jsx';
import { API } from './helpers/index.js';
import LoginForm from './components/LoginForm.jsx';
import Home from './pages/Home/index.jsx';
import NotFound from './pages/NotFound/index.jsx';
import UserSetting from './pages/Setting/index.jsx';
import Layout from './components/Layout.jsx';
import Forbidden from './pages/Forbidden/index.jsx';
import ChannelDetail from './pages/Feed/index.jsx';
import DashboardEpisodes from './pages/DashboardEpisodes/index.jsx';
import ShareEpisode from './pages/ShareEpisode/index.jsx';

function App() {
  const [, dispatch] = useContext(UserContext);

  const initializeAuth = async () => {
    try {
      const res = await API.get('/api/auth/status');
      const { code, data } = res.data;
      if (code !== 200) {
        return;
      }

      const isAuthEnabled = Boolean(data?.authEnabled);
      const authUser = data?.user || null;
      dispatch({ type: 'setAuthMode', payload: isAuthEnabled });

      if (!isAuthEnabled) {
        if (authUser) {
          dispatch({ type: 'login', payload: authUser });
          localStorage.setItem('user', JSON.stringify(authUser));
          return;
        }
        localStorage.removeItem('user');
        dispatch({ type: 'logout' });
        return;
      }

      if (authUser) {
        dispatch({ type: 'login', payload: authUser });
        localStorage.setItem('user', JSON.stringify(authUser));
        return;
      }

      if (!authUser) {
        localStorage.removeItem('user');
        dispatch({ type: 'logout' });
      }
    } catch {
      // Keep the current local session when auth status cannot be loaded.
    }
  };

  useEffect(() => {
    initializeAuth().then();
  }, []);

  return (
    <PlayerProvider>
      <Routes>
        <Route path="/share/episode/:episodeId" element={<ShareEpisode />} />
        <Route path="/" element={<Layout />}>
          <Route index element={<Home />} />
          <Route path="user-setting" element={<UserSetting />} />
          <Route path="/dashboard/episodes/:status" element={<DashboardEpisodes />} />
          <Route path="/:type/:feedId" element={<ChannelDetail />} />

          <Route path="login" element={<LoginForm />} />
          <Route path="403" element={<Forbidden />} />
          <Route path="*" element={<NotFound />} />
        </Route>
      </Routes>
    </PlayerProvider>
  );
}

export default App;
