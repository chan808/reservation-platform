import { defineRouting } from "next-intl/routing";

export const routing = defineRouting({
  locales: ["ko", "en"],
  defaultLocale: "ko",
  // 기본 locale(ko)은 prefix 없이 접근 가능: /verify-email, /login 등
  // 이메일 링크 등 외부 링크에서 locale을 몰라도 동작
  localePrefix: "as-needed",
});
