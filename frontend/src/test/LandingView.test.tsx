// @vitest-environment jsdom
import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { LandingView } from '../components/farmflate/LandingView';
import { ApiService } from '../services/api';

vi.mock('../services/api', () => ({
  ApiService: {
    getKakaoLoginAvailability: vi.fn()
  }
}));

describe('LandingView', () => {
  it('keeps a normal guest session quiet and blocks OAuth when Kakao is not configured', async () => {
    vi.mocked(ApiService.getKakaoLoginAvailability).mockResolvedValue({ configured: false });
    render(<LandingView />);

    fireEvent.click(screen.getByRole('button', { name: '카카오로 시작하기' }));

    expect(await screen.findByText('카카오 로그인 설정을 확인해 주세요.')).toBeInTheDocument();
    expect(screen.queryByText('인증이 필요합니다.')).not.toBeInTheDocument();
  });
});
