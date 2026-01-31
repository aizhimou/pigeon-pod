import React, { useContext, useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { UserContext } from '../context/User/UserContext.jsx';
import { API, showError, showWarning } from '../helpers';
import logo from '../assets/pigeonpod.svg';
import {
  Anchor,
  Button,
  Container,
  Group,
  Image,
  Paper,
  PasswordInput,
  Stack,
  TextInput,
  Title,
} from '@mantine/core';
import { useForm } from '@mantine/form';
import { useTranslation } from 'react-i18next';

const LoginForm = () => {
  const [searchParams] = useSearchParams();
  const [, dispatch] = useContext(UserContext);
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();
  const { t } = useTranslation();
  const [captchaEnabled, setCaptchaEnabled] = useState(false);
  const [captchaId, setCaptchaId] = useState('');
  const [captchaImage, setCaptchaImage] = useState('');
  const [captchaLoading, setCaptchaLoading] = useState(false);

  useEffect(() => {
    if (searchParams.get('expired')) {
      showWarning(t('not_logged_in_or_expired'));
    }
  }, []);

  const loginForm = useForm({
    initialValues: {
      username: '',
      password: '',
      captchaCode: '',
    },
    validate: {
      username: (value) =>
        value.length >= 3 && value.length <= 20
          ? null
          : 'Username must be between 3 and 20 characters',
      password: (value) =>
        value.length >= 6 ? null : 'Password must be at least 6 characters long',
    },
  });

  const refreshCaptcha = async () => {
    setCaptchaLoading(true);
    const res = await API.get('/api/auth/captcha');
    const { code, msg, data } = res.data;
    if (code !== 200) {
      showError(msg);
      setCaptchaLoading(false);
      return;
    }
    setCaptchaId(data.captchaId);
    setCaptchaImage(data.imageData);
    loginForm.setFieldValue('captchaCode', '');
    setCaptchaLoading(false);
  };

  const fetchCaptchaConfig = async () => {
    const res = await API.get('/api/auth/captcha-config');
    const { code, msg, data } = res.data;
    if (code !== 200) {
      showError(msg);
      return;
    }
    const enabled = Boolean(data);
    setCaptchaEnabled(enabled);
    if (enabled) {
      refreshCaptcha().then();
    } else {
      setCaptchaId('');
      setCaptchaImage('');
    }
  };

  useEffect(() => {
    fetchCaptchaConfig().then();
  }, []);

  const login = async () => {
    setLoading(true);
    const formValues = loginForm.getValues();
    if (captchaEnabled && !formValues.captchaCode) {
      showError(t('captcha_required'));
      setLoading(false);
      return;
    }
    const res = await API.post('/api/auth/login', {
      username: formValues.username,
      password: formValues.password,
      captchaId,
      captchaCode: formValues.captchaCode,
    });
    const { code, msg, data } = res.data;
    if (code !== 200) {
      showError(msg);
      if (captchaEnabled) {
        refreshCaptcha().then();
      }
      setLoading(false);
      return;
    }
    setLoading(false);
    dispatch({ type: 'login', payload: data });
    localStorage.setItem('user', JSON.stringify(data));
    navigate('/');
  };

  return (
    <Container pt="150px" size="xs">
      <Group justify="center">
        <Image src={logo} w={60}></Image>
        <Title>{t('header_title')}</Title>
      </Group>
      <Paper p="xl" withBorder mt="md">
        <form onSubmit={loginForm.onSubmit(login)}>
          <Stack>
            <TextInput
              name="username"
              label={t('username')}
              placeholder={t('username_placeholder')}
              key={loginForm.key('username')}
              {...loginForm.getInputProps('username')}
            />
            <PasswordInput
              name="password"
              label={t('password')}
              placeholder={t('password_placeholder')}
              key={loginForm.key('password')}
              {...loginForm.getInputProps('password')}
            />
            {captchaEnabled ? (
              <Group align="flex-end" gap="sm">
                <TextInput
                  name="captchaCode"
                  label={t('captcha')}
                  placeholder={t('captcha_placeholder')}
                  key={loginForm.key('captchaCode')}
                  {...loginForm.getInputProps('captchaCode')}
                  style={{ flex: 1 }}
                />
                <Image
                  src={captchaImage || undefined}
                  alt={t('captcha')}
                  w={120}
                  h={35}
                  radius="sm"
                  onClick={() => {
                    if (!captchaLoading) {
                      refreshCaptcha().then();
                    }
                  }}
                  style={{ cursor: 'pointer', opacity: captchaLoading ? 0.6 : 1 }}
                />
              </Group>
            ) : null}
            <Button
              type="submit"
              variant="gradient"
              loading={loading}
              gradient={{ from: '#ae2140', to: '#f28b96', deg: 1 }}
            >
              {t('login')}
            </Button>
          </Stack>
        </form>
      </Paper>
    </Container>
  );
};

export default LoginForm;
