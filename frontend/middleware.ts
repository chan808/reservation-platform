import createMiddleware from "next-intl/middleware";
import { routing } from "./src/i18n/routing";

// i18n 라우팅만 담당
// 인증 가드는 (main)/layout.tsx에서 처리:
//   RT 쿠키가 path=/api/auth로 제한되어 미들웨어에서 직접 읽기 불가
export default createMiddleware(routing);

export const config = {
  matcher: ["/((?!api|_next/static|_next/image|favicon.ico|.*\\..*).*)"],
};
