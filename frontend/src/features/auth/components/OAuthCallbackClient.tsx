"use client";

import { useEffect, useRef } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { useLocale } from "next-intl";
import { authApi } from "@/features/auth/api/authApi";
import { useAuthStore } from "@/features/auth/stores/authStore";

export default function OAuthCallbackClient() {
  const router = useRouter();
  const locale = useLocale();
  const searchParams = useSearchParams();
  const setAccessToken = useAuthStore((s) => s.setAccessToken);
  const hasExchanged = useRef(false);

  useEffect(() => {
    if (hasExchanged.current) return;
    hasExchanged.current = true;

    const code = searchParams.get("code");
    const error = searchParams.get("error");

    if (error) {
      router.replace(`/${locale}/login?error=${encodeURIComponent(error)}`);
      return;
    }

    if (!code) {
      router.replace(`/${locale}/login`);
      return;
    }

    authApi
      .exchangeOAuthCode(code)
      .then((res) => {
        const at = res.data.data?.accessToken;
        if (at) setAccessToken(at);
        router.replace(`/${locale}/dashboard`);
      })
      .catch(() => {
        router.replace(`/${locale}/login?error=${encodeURIComponent("OAuth login failed.")}`);
      });
  }, [locale, router, searchParams, setAccessToken]);

  return (
    <main className="flex min-h-screen items-center justify-center">
      <p className="text-muted-foreground">Processing login...</p>
    </main>
  );
}
