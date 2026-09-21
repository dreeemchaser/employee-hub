import { useCallback, useMemo, useState } from 'react';

const KEY_PREFIX = 'dashboard-layout';

function storageKey(accountKey) {
  return `${KEY_PREFIX}:${accountKey}`;
}

// Read the saved layout, tolerating absent/corrupt data by returning null so the
// caller falls back to the default (today's order, nothing hidden).
function readSaved(accountKey) {
  try {
    const raw = localStorage.getItem(storageKey(accountKey));
    if (!raw) return null;
    const parsed = JSON.parse(raw);
    if (!parsed || !Array.isArray(parsed.order) || !Array.isArray(parsed.hidden)) return null;
    return { order: parsed.order, hidden: parsed.hidden };
  } catch {
    return null;
  }
}

function writeSaved(accountKey, order, hidden) {
  try {
    localStorage.setItem(storageKey(accountKey), JSON.stringify({ order, hidden }));
  } catch {
    // localStorage may be unavailable (private mode / quota) — layout is cosmetic,
    // so silently degrade to in-memory-only for this session.
  }
}

/**
 * Client-side dashboard layout preference, persisted per account in localStorage.
 *
 * @param {string} accountKey   stable per-account identifier (see AuthService.getAccountKey)
 * @param {string[]} allCardIds every card id available on the dashboard, in the
 *                              default display order — this IS the default layout
 *                              when nothing is saved, and the source of truth for
 *                              reconciling stale saved orders.
 * @returns {{
 *   order: string[],
 *   hidden: string[],
 *   isHidden: (id: string) => boolean,
 *   toggleHidden: (id: string) => void,
 *   moveUp: (id: string) => void,
 *   moveDown: (id: string) => void,
 *   reset: () => void,
 *   orderedVisible: (candidateIds: string[]) => string[],
 * }}
 */
export function useDashboardLayout(accountKey, allCardIds) {
  const saved = useMemo(() => readSaved(accountKey), [accountKey]);

  // Reconcile any saved order against the current card list: keep saved ids that
  // still exist (in their saved position), then append any new ids not yet saved.
  // This makes old saved layouts degrade gracefully when a card is added/removed.
  const initialOrder = useMemo(() => {
    if (!saved) return allCardIds;
    const known = new Set(allCardIds);
    const kept = saved.order.filter(id => known.has(id));
    const appended = allCardIds.filter(id => !kept.includes(id));
    return [...kept, ...appended];
  }, [saved, allCardIds]);

  const initialHidden = useMemo(() => {
    if (!saved) return [];
    const known = new Set(allCardIds);
    return saved.hidden.filter(id => known.has(id));
  }, [saved, allCardIds]);

  const [order, setOrder] = useState(initialOrder);
  const [hidden, setHidden] = useState(initialHidden);

  const persist = useCallback((nextOrder, nextHidden) => {
    setOrder(nextOrder);
    setHidden(nextHidden);
    writeSaved(accountKey, nextOrder, nextHidden);
  }, [accountKey]);

  const isHidden = useCallback(id => hidden.includes(id), [hidden]);

  const toggleHidden = useCallback(id => {
    const next = hidden.includes(id) ? hidden.filter(h => h !== id) : [...hidden, id];
    persist(order, next);
  }, [hidden, order, persist]);

  const moveUp = useCallback(id => {
    const idx = order.indexOf(id);
    if (idx <= 0) return;
    const next = [...order];
    [next[idx - 1], next[idx]] = [next[idx], next[idx - 1]];
    persist(next, hidden);
  }, [order, hidden, persist]);

  const moveDown = useCallback(id => {
    const idx = order.indexOf(id);
    if (idx < 0 || idx >= order.length - 1) return;
    const next = [...order];
    [next[idx + 1], next[idx]] = [next[idx], next[idx + 1]];
    persist(next, hidden);
  }, [order, hidden, persist]);

  const reset = useCallback(() => {
    persist(allCardIds, []);
  }, [allCardIds, persist]);

  // Given the ids of cards currently eligible to show (data-dependent cards may
  // be absent), return them filtered by `hidden` and sorted by `order`. Ids not
  // in `order` sort to the end.
  const orderedVisible = useCallback(candidateIds => {
    const rank = id => {
      const i = order.indexOf(id);
      return i === -1 ? Number.MAX_SAFE_INTEGER : i;
    };
    return candidateIds
      .filter(id => !hidden.includes(id))
      .sort((a, b) => rank(a) - rank(b));
  }, [order, hidden]);

  return { order, hidden, isHidden, toggleHidden, moveUp, moveDown, reset, orderedVisible };
}
