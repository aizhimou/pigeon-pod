import React, { useContext } from 'react';
import {
  ActionIcon,
  Group,
  Image,
  Menu,
  Paper,
  Text,
  useComputedColorScheme,
  useMantineColorScheme,
} from '@mantine/core';
import logo from '../assets/pigeonpod.svg';
import {
  IconBrandGithub,
  IconLanguage,
  IconLogout2,
  IconMoon,
  IconSettings,
  IconSun,
} from '@tabler/icons-react';
import { useMediaQuery } from '@mantine/hooks';
import { API, showSuccess } from '../helpers/index.js';
import { useNavigate } from 'react-router-dom';
import { UserContext } from '../context/User/UserContext.jsx';
import { useTranslation } from 'react-i18next';

function Header() {
  const isSmallScreen = useMediaQuery('(max-width: 36em)');
  const computedColorScheme = useComputedColorScheme();
  const { colorScheme, setColorScheme } = useMantineColorScheme();
  const toggleColorScheme = () => {
    setColorScheme(colorScheme === 'dark' ? 'light' : 'dark');
  };
  const [state, dispatch] = useContext(UserContext);
  const navigate = useNavigate();
  const { i18n, t } = useTranslation();

  function changeLanguageWithStorage(lng) {
    i18n.changeLanguage(lng);
    localStorage.setItem('language', lng);
  }

  async function logout() {
    await API.post('/api/auth/logout');
    dispatch({ type: 'logout' });
    localStorage.removeItem('user');
    showSuccess(t('logout_success'));
    navigate('/login');
  }

  return (
    <Paper
      shadow="sm"
      p={5}
      pos="sticky"
      style={{ top: 0, zIndex: 100, position: 'sticky' }}
    >
      <Group justify="space-between" mx={isSmallScreen ? 'xs' : 'xl'}>
        <Group gap="xs" mr={10} onClick={() => navigate('/')} style={{ cursor: 'pointer' }}>
          <Image src={logo} w={40}></Image>
          {/*<Title order={4}>{t('header_title')}</Title>*/}
        </Group>
        <Group>
          <Menu>
            <Menu.Target>
              <ActionIcon variant="default" size="sm">
                <IconLanguage />
              </ActionIcon>
            </Menu.Target>
            <Menu.Dropdown>
              <Menu.Item onClick={() => changeLanguageWithStorage('en')}>
                {t('header_lang_en')}
              </Menu.Item>
              <Menu.Item onClick={() => changeLanguageWithStorage('zh')}>
                {t('header_lang_zh')}
              </Menu.Item>
              <Menu.Item onClick={() => changeLanguageWithStorage('es')}>
                {t('header_lang_es')}
              </Menu.Item>
              <Menu.Item onClick={() => changeLanguageWithStorage('ja')}>
                {t('header_lang_ja')}
              </Menu.Item>
              <Menu.Item onClick={() => changeLanguageWithStorage('pt')}>
                {t('header_lang_pt')}
              </Menu.Item>
              <Menu.Item onClick={() => changeLanguageWithStorage('fr')}>
                {t('header_lang_fr')}
              </Menu.Item>
              <Menu.Item onClick={() => changeLanguageWithStorage('de')}>
                {t('header_lang_de')}
              </Menu.Item>
              <Menu.Item onClick={() => changeLanguageWithStorage('ko')}>
                {t('header_lang_ko')}
              </Menu.Item>
            </Menu.Dropdown>
          </Menu>
          <ActionIcon variant="default" size="sm">
            {'dark' === computedColorScheme ? (
              <IconSun onClick={toggleColorScheme} />
            ) : (
              <IconMoon onClick={toggleColorScheme} />
            )}
          </ActionIcon>
          <ActionIcon
            variant="default"
            size="sm"
            component="a"
            href="https://github.com/aizhimou/PigeonPod/"
            target="_blank"
          >
            <IconBrandGithub />
          </ActionIcon>
          {state.user ? (
            <Menu withArrow>
              <Menu.Target>
                <Group gap={0} style={{ cursor: 'pointer' }}>
                  <Text fw={600}>{state.user.username}</Text>
                </Group>
              </Menu.Target>
              <Menu.Dropdown>
                <Menu.Item
                  leftSection={<IconSettings size={14} />}
                  onClick={() => navigate('/user-setting')}
                >
                  {t('header_account')}
                </Menu.Item>
                <Menu.Item leftSection={<IconLogout2 size={14} />} onClick={logout}>
                  {t('header_logout')}
                </Menu.Item>
              </Menu.Dropdown>
            </Menu>
          ) : (
            <></>
          )}
        </Group>
      </Group>
    </Paper>
  );
}

export default Header;
