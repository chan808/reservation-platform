"use client";

import { useEffect, useRef } from "react";
import { useRouter } from "next/navigation";
import { useLocale } from "next-intl";
import { authApi } from "@/features/auth/api/authApi";
import { useAuthStore } from "@/features/auth/stores/authStore";

export default function LocaleRootPage() {
  const router = useRouter();
  const locale = useLocale();
  const { setAccessToken, clearAuth } = useAuthStore();
  const hasChecked = useRef(false);

  useEffect(() => {
    if (hasChecked.current) return;
    hasChecked.current = true;

    if (useAuthStore.getState().accessToken) {
      router.replace(`/${locale}/dashboard`);
      return;
    }

    authApi
      .reissue()
      .then(({ data }) => {
        setAccessToken(data.data!.accessToken);
        router.replace(`/${locale}/dashboard`);
      })
      .catch(() => {
        clearAuth();
        router.replace(`/${locale}/login`);
      });
  }, [clearAuth, locale, router, setAccessToken]);

  return (
    <main className="flex min-h-screen items-center justify-center">
      <p className="text-muted-foreground">Loading...</p>
    </main>
  );
}
