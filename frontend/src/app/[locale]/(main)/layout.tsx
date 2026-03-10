"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useLocale } from "next-intl";
import { useAuthStore } from "@/features/auth/stores/authStore";
import { authApi } from "@/features/auth/api/authApi";

// Spring Security 필터와 동일한 역할: 비인증 접근 차단
// AT는 Zustand(메모리)에만 존재 → 미들웨어 대신 레이아웃에서 검증
export default function MainLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const router = useRouter();
  const locale = useLocale();
  const { setAccessToken, clearAuth } = useAuthStore();
  const [ready, setReady] = useState(false);

  useEffect(() => {
    // getState(): 구독 없이 현재 값 일회성 확인 — 마운트 이후 accessToken 변경에 반응할 필요 없음
    if (useAuthStore.getState().accessToken) {
      setReady(true);
      return;
    }
    // 페이지 새로고침 시 RT 쿠키로 AT 복구
    authApi
      .reissue()
      .then(({ data }) => {
        setAccessToken(data.data!.accessToken);
        setReady(true);
      })
      .catch(() => {
        clearAuth();
        router.replace(`/${locale}/login`);
      });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  if (!ready) return null;

  return <>{children}</>;
}
