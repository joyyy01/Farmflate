import React from 'react';

interface DefaultUserAvatarProps {
  size?: number;
  label?: string;
}

export const DefaultUserAvatar: React.FC<DefaultUserAvatarProps> = ({ size = 40, label = '기본 프로필' }) => (
  <img
    src="/assets/mypage-profile-mascot.png"
    alt={label}
    width={size}
    height={size}
    style={{ borderRadius: '50%', objectFit: 'cover', flexShrink: 0 }}
  />
);
