import { useEffect } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';

export default function OAuth2Callback() {
  const navigate = useNavigate();
  const location = useLocation();

  useEffect(() => {
    const params = new URLSearchParams(location.search);
    const token = params.get('token');

    if (token) {
      localStorage.setItem('jwtToken', token);
      navigate('/?view=dashboard', { replace: true });
    } else {
      navigate('/', { replace: true });
    }
  }, [location, navigate]);

  return (
    <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100vh', flexDirection: 'column' }}>
      <h2 style={{ color: '#2FA86A' }}>로그인 처리 중입니다...</h2>
      <p>잠시만 기다려 주세요.</p>
    </div>
  );
}
