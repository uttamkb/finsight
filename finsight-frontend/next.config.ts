import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  /* config options here */
  experimental: {
    // @ts-ignore
    turbopack: {
      root: __dirname,
    },
  },
};

export default nextConfig;
