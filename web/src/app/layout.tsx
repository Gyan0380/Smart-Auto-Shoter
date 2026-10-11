import type { Metadata } from "next";
import "./globals.css";
export const metadata: Metadata = { title: "Smart Auto Sorter Community", description: "Create, discover and share Minecraft inventory layouts." };
export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) { return <html lang="en"><body>{children}</body></html>; }
