"use client";

import { useEffect, useState } from "react";
import { useSearchParams } from "next/navigation";
import { useTranslations, useLocale } from "next-intl";
import Link from "next/link";
import { authApi } from "@/features/auth/api/authApi";
import { Button } from "@/shared/components/ui/button";
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
} from "@/shared/components/ui/card";

type Status = "loading" | "success" | "error";

export default function VerifyEmailResult() {
  const t = useTranslations("auth.verifyEmail");
  const locale = useLocale();
  const searchParams = useSearchParams();
  const token = searchParams.get("token");
  const [status, setStatus] = useState<Status>(token ? "loading" : "error");

  useEffect(() => {
    if (!token) return;
    authApi
      .verifyEmail(token)
      .then(() => setStatus("success"))
      .catch(() => setStatus("error"));
  }, [token]);

  return (
    <Card className="w-full max-w-md text-center">
      <CardHeader>
        <CardTitle className="text-2xl">{t(`${status}.title`)}</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        <p className="text-muted-foreground">{t(`${status}.description`)}</p>
        {status === "success" && (
          <Button asChild className="w-full">
            <Link href={`/${locale}/login`}>{t("success.action")}</Link>
          </Button>
        )}
      </CardContent>
    </Card>
  );
}
