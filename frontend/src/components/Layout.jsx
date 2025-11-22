import Header from './Header.jsx';
import { Outlet } from 'react-router-dom';
import '@mantine/core/styles.layer.css';
import 'mantine-datatable/styles.layer.css';
import GlobalPlayer from './GlobalPlayer';

const Layout = () => {
  return (
    <>
      <Header />
      <main>
        <Outlet />
      </main>
      <GlobalPlayer />
    </>
  );
};

export default Layout;
