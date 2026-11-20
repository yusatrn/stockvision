import Link from "next/link";
import { Compass } from "lucide-react";
import { buttonClasses } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";

export default function NotFoundPage() {
  return (
    <div className="flex min-h-screen items-center justify-center bg-background p-6">
      <Card className="w-full max-w-md">
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Compass className="h-5 w-5 text-accent" aria-hidden="true" />
            <span>Sayfa Bulunamadı (404)</span>
          </CardTitle>
          <CardDescription>
            Aradığınız StockVision AI görünümü veya adresi mevcut değil.
          </CardDescription>
        </CardHeader>
        <CardContent className="flex flex-wrap gap-2">
          <Link
            href="/"
            className={buttonClasses({ variant: "primary", size: "sm" })}
          >
            Kontrol Paneline Git
          </Link>
          <Link
            href="/markets"
            className={buttonClasses({ variant: "outline", size: "sm" })}
          >
            Piyasalara Göz At
          </Link>
        </CardContent>
      </Card>
    </div>
  );
}
