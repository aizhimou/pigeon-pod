import { useContext, useEffect } from 'react';
import { Route, Routes } from 'react-router-dom';
import { UserContext } from './context/User/UserContext.jsx';
import { PlayerProvider } from './context/PlayerContext.jsx';
import LoginForm from './components/LoginForm.jsx';
import Home from './pages/Home/index.jsx';
import NotFound from './pages/NotFound/index.jsx';
import UserSetting from './pages/UserSetting/index.jsx';
import Layout from './components/Layout.jsx';
import Forbidden from './pages/Forbidden/index.jsx';
import ChannelDetail from './pages/Feed/index.jsx';
import DashboardEpisodes from './pages/DashboardEpisodes/index.jsx';

function App() {
  const [, dispatch] = useContext(UserContext);

  const loadUser = async () => {
    let user = localStorage.getItem('user');
    if (user) {
      let data = JSON.parse(user);
      dispatch({ type: 'login', payload: data });
    }
  };

  useEffect(() => {
    loadUser().then();
  }, []);

  return (
    <PlayerProvider>
      <Routes>
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
