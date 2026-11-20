"use client";

import React, { useCallback, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import {
  BellPlus,
  Eye,
  FolderPlus,
  Pencil,
  Plus,
  RefreshCw,
  Trash2,
} from "lucide-react";
import { useAuth } from "@/components/providers/auth-provider";
import {
  DataProvenanceBar,
  DecisionSupportDisclaimer,
  SignedPercentBadge,
} from "@/components/shared/data-status-badges";
import { Alert } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Input, Label } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { isApiClientError } from "@/lib/api/client";
import { apiServices } from "@/lib/api/services";
import type { WatchlistCollectionResponse } from "@/lib/api/types";
import {
  formatCompactNumber,
  formatCurrency,
  formatDateTime,
} from "@/lib/format";
import { translateSector } from "@/lib/labels";

export function WatchlistView() {
  const { effectiveTimezone } = useAuth();

  const [collection, setCollection] = useState<WatchlistCollectionResponse | null>(null);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [actionMessage, setActionMessage] = useState<string | null>(null);

  const [newListName, setNewListName] = useState("");
  const [newListDescription, setNewListDescription] = useState("");
  const [creatingList, setCreatingList] = useState(false);

  const [renaming, setRenaming] = useState(false);
  const [renameDraft, setRenameDraft] = useState("");
  const [renameDescriptionDraft, setRenameDescriptionDraft] = useState("");

  const [addSymbol, setAddSymbol] = useState("");
  const [addNotes, setAddNotes] = useState("");
  const [addingItem, setAddingItem] = useState(false);

  const loadWatchlists = useCallback(async (signal?: AbortSignal) => {
    setLoading(true);
    setErrorMessage(null);
    try {
      const data = await apiServices.watchlists.list(signal);
      setCollection(data);
      setSelectedId((prev) => {
        if (prev && data.watchlists.some((w) => w.id === prev)) {
          return prev;
        }
        return data.watchlists[0]?.id ?? null;
      });
    } catch (err) {
      if (err instanceof DOMException && err.name === "AbortError") {
        return;
      }
      setErrorMessage(
        isApiClientError(err)
          ? `${err.message} (${err.code})`
          : "İzleme listeleri yüklenemedi."
      );
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    loadWatchlists(controller.signal);
    return () => controller.abort();
  }, [loadWatchlists]);

  const activeWatchlist = useMemo(() => {
    if (!collection || collection.watchlists.length === 0) {
      return null;
    }
    return (
      collection.watchlists.find((w) => w.id === selectedId) ??
      collection.watchlists[0]
    );
  }, [collection, selectedId]);

  const handleCreateList = async (event: React.FormEvent) => {
    event.preventDefault();
    setErrorMessage(null);
    setActionMessage(null);
    if (!newListName.trim()) {
      setErrorMessage("İzleme listesi adı zorunludur.");
      return;
    }
    setCreatingList(true);
    try {
      const created = await apiServices.watchlists.create({
        name: newListName.trim(),
        description: newListDescription.trim() || undefined,
      });
      setNewListName("");
      setNewListDescription("");
      setSelectedId(created.id);
      setActionMessage(`"${created.name}" izleme listesi oluşturuldu.`);
      await loadWatchlists();
    } catch (err) {
      setErrorMessage(
        isApiClientError(err) ? err.message : "İzleme listesi oluşturulamadı."
      );
    } finally {
      setCreatingList(false);
    }
  };

  const handleStartRename = () => {
    if (!activeWatchlist) {
      return;
    }
    setRenameDraft(activeWatchlist.name);
    setRenameDescriptionDraft(activeWatchlist.description ?? "");
    setRenaming(true);
  };

  const handleSaveRename = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!activeWatchlist) {
      return;
    }
    setErrorMessage(null);
    setActionMessage(null);
    try {
      const updated = await apiServices.watchlists.update(activeWatchlist.id, {
        name: renameDraft.trim(),
        description: renameDescriptionDraft.trim() || undefined,
      });
      setRenaming(false);
      setActionMessage(`İzleme listesi adı "${updated.name}" olarak güncellendi.`);
      await loadWatchlists();
    } catch (err) {
      setErrorMessage(
        isApiClientError(err) ? err.message : "İzleme listesi güncellenemedi."
      );
    }
  };

  const handleDeleteList = async (id: string, name: string) => {
    setErrorMessage(null);
    setActionMessage(null);
    try {
      await apiServices.watchlists.remove(id);
      setRenaming(false);
      setActionMessage(`"${name}" izleme listesi silindi.`);
      await loadWatchlists();
    } catch (err) {
      setErrorMessage(
        isApiClientError(err) ? err.message : "İzleme listesi silinemedi."
      );
    }
  };

  const handleAddItem = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!activeWatchlist) {
      return;
    }
    setErrorMessage(null);
    setActionMessage(null);
    const symbol = addSymbol.trim().toUpperCase();
    if (!symbol) {
      setErrorMessage("Hisse sembolü zorunludur.");
      return;
    }
    setAddingItem(true);
    try {
      const item = await apiServices.watchlists.addItem(activeWatchlist.id, {
        symbol,
        notes: addNotes.trim() || undefined,
      });
      setAddSymbol("");
      setAddNotes("");
      setActionMessage(`${item.symbol}, "${activeWatchlist.name}" listesine eklendi.`);
      await loadWatchlists();
    } catch (err) {
      setErrorMessage(
        isApiClientError(err) ? err.message : `${symbol} listeye eklenemedi.`
      );
    } finally {
      setAddingItem(false);
    }
  };

  const handleRemoveItem = async (itemId: string, symbol: string) => {
    if (!activeWatchlist) {
      return;
    }
    setErrorMessage(null);
    setActionMessage(null);
    try {
      await apiServices.watchlists.removeItem(activeWatchlist.id, itemId);
      setActionMessage(`${symbol}, "${activeWatchlist.name}" listesinden çıkarıldı.`);
      await loadWatchlists();
    } catch (err) {
      setErrorMessage(
        isApiClientError(err) ? err.message : `${symbol} listeden çıkarılamadı.`
      );
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-xl font-bold tracking-tight sm:text-2xl">
            İzleme Listeleri
          </h1>
          <p className="text-sm text-muted">
            Hesabınıza özel birden fazla adlandırılmış izleme listesini çift kayıt korumasıyla yönetin.
          </p>
        </div>
        <Button
          variant="outline"
          size="sm"
          onClick={() => loadWatchlists()}
          disabled={loading}
        >
          <RefreshCw
            className={loading ? "h-4 w-4 animate-spin" : "h-4 w-4"}
            aria-hidden="true"
          />
          <span>Fiyatları Yenile</span>
        </Button>
      </div>

      {errorMessage ? (
        <Alert variant="error" title="İzleme Listesi İşlem Hatası">
          {errorMessage}
        </Alert>
      ) : null}

      {actionMessage ? (
        <Alert variant="positive" title="İşlem Başarılı">
          {actionMessage}
        </Alert>
      ) : null}

      {collection ? (
        <DataProvenanceBar
          dataSource={collection.dataSource}
          freshness={collection.freshness}
          timezone={effectiveTimezone}
          extraLabel={`${collection.watchlists.length} liste`}
        />
      ) : null}

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <FolderPlus className="h-4 w-4 text-accent" aria-hidden="true" />
            <span>Yeni İzleme Listesi Oluştur</span>
          </CardTitle>
          <CardDescription>
            Hesabınızda aynı isimle (büyük/küçük harf duyarsız) ikinci bir liste oluşturulamaz.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleCreateList} className="grid gap-3 sm:grid-cols-3">
            <div className="space-y-1">
              <Label htmlFor="wl-new-name">Liste Adı</Label>
              <Input
                id="wl-new-name"
                value={newListName}
                onChange={(e) => setNewListName(e.target.value)}
                placeholder="ör. BIST Bankacılık"
                maxLength={80}
              />
            </div>
            <div className="space-y-1">
              <Label htmlFor="wl-new-desc">Açıklama (isteğe bağlı)</Label>
              <Input
                id="wl-new-desc"
                value={newListDescription}
                onChange={(e) => setNewListDescription(e.target.value)}
                placeholder="ör. Büyük ölçekli bankalar"
                maxLength={255}
              />
            </div>
            <div className="flex items-end">
              <Button type="submit" isLoading={creatingList} className="w-full sm:w-auto">
                <Plus className="h-4 w-4" aria-hidden="true" />
                <span>Liste Oluştur</span>
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>

      {loading && !collection ? (
        <Skeleton className="h-64 w-full" />
      ) : collection && collection.watchlists.length === 0 ? (
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Eye className="h-4 w-4 text-accent" aria-hidden="true" />
              <span>Henüz İzleme Listesi Oluşturulmadı</span>
            </CardTitle>
            <CardDescription>
              BIST sembollerini takip etmeye başlamak için yukarıdan ilk listenizi oluşturun.
            </CardDescription>
          </CardHeader>
        </Card>
      ) : activeWatchlist ? (
        <div className="space-y-4">
          <div
            role="tablist"
            aria-label="İzleme listeleriniz"
            className="flex flex-wrap items-center gap-2"
          >
            {collection?.watchlists.map((wl) => {
              const selected = wl.id === activeWatchlist.id;
              return (
                <button
                  key={wl.id}
                  type="button"
                  role="tab"
                  aria-selected={selected}
                  onClick={() => {
                    setSelectedId(wl.id);
                    setRenaming(false);
                  }}
                  className={`inline-flex items-center gap-2 rounded-md border px-3 py-1.5 text-sm font-medium transition-colors ${
                    selected
                      ? "border-accent bg-accent/15 text-accent"
                      : "border-border bg-surface text-muted hover:text-foreground"
                  }`}
                >
                  <span>{wl.name}</span>
                  <Badge variant="neutral">{wl.items.length}</Badge>
                </button>
              );
            })}
          </div>

          <Card>
            <CardHeader>
              <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                <div>
                  <CardTitle>{activeWatchlist.name}</CardTitle>
                  <CardDescription>
                    {activeWatchlist.description || "Açıklama girilmedi."} ·{" "}
                    {activeWatchlist.items.length} takip edilen enstrüman
                  </CardDescription>
                </div>
                <div className="flex flex-wrap gap-2">
                  <Button variant="outline" size="sm" onClick={handleStartRename}>
                    <Pencil className="h-3.5 w-3.5" aria-hidden="true" />
                    <span>Yeniden Adlandır</span>
                  </Button>
                  <Button
                    variant="danger"
                    size="sm"
                    onClick={() =>
                      handleDeleteList(activeWatchlist.id, activeWatchlist.name)
                    }
                  >
                    <Trash2 className="h-3.5 w-3.5" aria-hidden="true" />
                    <span>Listeyi Sil</span>
                  </Button>
                </div>
              </div>
            </CardHeader>
            <CardContent className="space-y-5">
              {renaming ? (
                <form
                  onSubmit={handleSaveRename}
                  className="grid gap-3 rounded-md border border-border bg-surface-muted/40 p-3 sm:grid-cols-3"
                >
                  <div className="space-y-1">
                    <Label htmlFor="wl-rename">Yeni Ad</Label>
                    <Input
                      id="wl-rename"
                      value={renameDraft}
                      onChange={(e) => setRenameDraft(e.target.value)}
                      required
                    />
                  </div>
                  <div className="space-y-1">
                    <Label htmlFor="wl-redesc">Yeni Açıklama</Label>
                    <Input
                      id="wl-redesc"
                      value={renameDescriptionDraft}
                      onChange={(e) => setRenameDescriptionDraft(e.target.value)}
                    />
                  </div>
                  <div className="flex items-end gap-2">
                    <Button type="submit" size="sm">
                      Değişiklikleri Kaydet
                    </Button>
                    <Button
                      type="button"
                      variant="ghost"
                      size="sm"
                      onClick={() => setRenaming(false)}
                    >
                      İptal
                    </Button>
                  </div>
                </form>
              ) : null}

              <form onSubmit={handleAddItem} className="grid gap-3 sm:grid-cols-4">
                <div className="space-y-1">
                  <Label htmlFor="wl-add-symbol">Hisse Sembolü Ekle</Label>
                  <Input
                    id="wl-add-symbol"
                    value={addSymbol}
                    onChange={(e) => setAddSymbol(e.target.value.toUpperCase())}
                    placeholder="ör. THYAO"
                    className="uppercase placeholder:normal-case"
                    maxLength={24}
                  />
                </div>
                <div className="space-y-1 sm:col-span-2">
                  <Label htmlFor="wl-add-notes">Not (isteğe bağlı)</Label>
                  <Input
                    id="wl-add-notes"
                    value={addNotes}
                    onChange={(e) => setAddNotes(e.target.value)}
                    placeholder="ör. SMA20 yakınında destek takibi"
                    maxLength={255}
                  />
                </div>
                <div className="flex items-end">
                  <Button type="submit" isLoading={addingItem} className="w-full">
                    <Plus className="h-4 w-4" aria-hidden="true" />
                    <span>Sembol Ekle</span>
                  </Button>
                </div>
              </form>

              {activeWatchlist.items.length > 0 ? (
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Sembol</TableHead>
                      <TableHead>Not</TableHead>
                      <TableHead className="text-right">Fiyat</TableHead>
                      <TableHead className="text-right">Değişim</TableHead>
                      <TableHead className="text-right">Hacim</TableHead>
                      <TableHead>Fiyat Zamanı</TableHead>
                      <TableHead className="text-right">İşlemler</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {activeWatchlist.items.map((item) => (
                      <TableRow key={item.id}>
                        <TableCell>
                          <Link
                            href={`/instruments/${encodeURIComponent(item.symbol)}`}
                            className="font-semibold text-accent hover:underline"
                          >
                            {item.symbol}
                          </Link>
                          <p className="text-xs text-muted line-clamp-1">
                            {item.name}
                            {item.sector ? ` · ${translateSector(item.sector)}` : ""}
                          </p>
                        </TableCell>
                        <TableCell className="text-xs text-muted">
                          {item.notes || "—"}
                        </TableCell>
                        <TableCell className="text-right font-medium tabular-nums">
                          {item.latestQuote
                            ? formatCurrency(
                                item.latestQuote.price,
                                item.latestQuote.currency
                              )
                            : "Kullanılamıyor"}
                        </TableCell>
                        <TableCell className="text-right">
                          {item.latestQuote ? (
                            <SignedPercentBadge value={item.latestQuote.changePercent} />
                          ) : (
                            <span className="text-xs text-muted">—</span>
                          )}
                        </TableCell>
                        <TableCell className="text-right tabular-nums text-muted">
                          {item.latestQuote
                            ? formatCompactNumber(item.latestQuote.volume)
                            : "—"}
                        </TableCell>
                        <TableCell className="whitespace-nowrap text-xs text-muted tabular-nums">
                          {item.latestQuote
                            ? formatDateTime(item.latestQuote.quotedAt, effectiveTimezone)
                            : "—"}
                        </TableCell>
                        <TableCell className="text-right">
                          <div className="inline-flex items-center gap-1">
                            <Link
                              href={`/alerts?symbol=${encodeURIComponent(item.symbol)}`}
                              aria-label={`${item.symbol} için alarm oluştur`}
                              title="Alarm Oluştur"
                              className="inline-flex h-8 w-8 items-center justify-center rounded-md border border-border hover:bg-surface-muted"
                            >
                              <BellPlus className="h-3.5 w-3.5 text-accent" aria-hidden="true" />
                            </Link>
                            <Button
                              variant="ghost"
                              size="icon"
                              aria-label={`${item.symbol} sembolünü izleme listesinden çıkar`}
                              onClick={() => handleRemoveItem(item.id, item.symbol)}
                            >
                              <Trash2 className="h-3.5 w-3.5 text-negative" aria-hidden="true" />
                            </Button>
                          </div>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              ) : (
                <div className="rounded-md border border-dashed border-border p-6 text-center text-sm text-muted">
                  Bu izleme listesinde henüz enstrüman bulunmuyor. Yukarıdan bir sembol ekleyin (örneğin `THYAO` veya `GARAN`).
                </div>
              )}
            </CardContent>
          </Card>
        </div>
      ) : null}

      <DecisionSupportDisclaimer />
    </div>
  );
}
