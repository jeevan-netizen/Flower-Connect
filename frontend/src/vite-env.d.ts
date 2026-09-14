/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL?: string;
  readonly VITE_APP_TITLE?: string;
}

declare module "*.svg" {
  const src: string;
  export default src;
}