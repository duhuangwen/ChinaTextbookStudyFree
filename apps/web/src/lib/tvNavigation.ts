export type TvDirection = "up" | "down" | "left" | "right";

export interface FocusGeometry {
  left: number;
  top: number;
  right: number;
  bottom: number;
  /** Primary action (e.g. "检查") wins ties against siblings in the same row. */
  primary?: boolean;
}

/** Off-axis offset may be at most this many times the on-axis offset. */
const MAX_CONE_RATIO = 2;
/** Candidates whose edge distance is within this many px count as one row. */
const SAME_ROW_TOLERANCE = 24;

function centerX(rect: FocusGeometry): number {
  return (rect.left + rect.right) / 2;
}

function centerY(rect: FocusGeometry): number {
  return (rect.top + rect.bottom) / 2;
}

/**
 * Pick the closest visible control in one direction.
 *
 * The primary axis distance dominates. Orthogonal distance and overlap break
 * ties, which makes two-dimensional card grids feel predictable with a D-pad.
 * Controls far off-axis are ignored, so pressing toward an empty edge stays put
 * instead of teleporting to the other side of the page.
 */
export function pickDirectionalCandidate<T extends FocusGeometry>(
  current: FocusGeometry,
  candidates: readonly T[],
  direction: TvDirection,
): T | null {
  const currentX = centerX(current);
  const currentY = centerY(current);
  let best: T | null = null;
  let bestScore = Number.POSITIVE_INFINITY;
  let bestEdge = Number.POSITIVE_INFINITY;
  const primaries: { candidate: T; edge: number }[] = [];
  // Vertical moves fall back to off-cone controls fully past the current edge, so a footer action
  // under another column (e.g. GradePicker's "继续") stays reachable. Horizontal moves keep the cone
  // to avoid jumping from content to the sidebar.
  let fallback: T | null = null;
  let fallbackScore = Number.POSITIVE_INFINITY;

  for (const candidate of candidates) {
    const candidateX = centerX(candidate);
    const candidateY = centerY(candidate);

    let primary = 0;
    let secondary = 0;
    let overlaps = true;

    if (direction === "left") {
      primary = current.left - candidate.right;
      if (candidateX >= currentX - 2) continue;
      secondary = Math.abs(candidateY - currentY);
      overlaps = candidate.bottom > current.top && candidate.top < current.bottom;
    } else if (direction === "right") {
      primary = candidate.left - current.right;
      if (candidateX <= currentX + 2) continue;
      secondary = Math.abs(candidateY - currentY);
      overlaps = candidate.bottom > current.top && candidate.top < current.bottom;
    } else if (direction === "up") {
      primary = current.top - candidate.bottom;
      if (candidateY >= currentY - 2) continue;
      secondary = Math.abs(candidateX - currentX);
      overlaps = candidate.right > current.left && candidate.left < current.right;
    } else {
      primary = candidate.top - current.bottom;
      if (candidateY <= currentY + 2) continue;
      secondary = Math.abs(candidateX - currentX);
      overlaps = candidate.right > current.left && candidate.left < current.right;
    }

    const axial = direction === "left" || direction === "right"
      ? Math.abs(candidateX - currentX)
      : Math.abs(candidateY - currentY);
    const edgeDistance = Math.max(0, primary);
    const score = edgeDistance + secondary * 1.8 + (overlaps ? 0 : 120);
    if (!overlaps && secondary > axial * MAX_CONE_RATIO) {
      const vertical = direction === "up" || direction === "down";
      if (vertical && primary >= 0 && score < fallbackScore) {
        fallback = candidate;
        fallbackScore = score;
      }
      continue;
    }
    if (candidate.primary && overlaps) primaries.push({ candidate, edge: edgeDistance });
    if (score < bestScore) {
      best = candidate;
      bestScore = score;
      bestEdge = edgeDistance;
    }
  }

  const rowPrimary = primaries.find(p => p.edge <= bestEdge + SAME_ROW_TOLERANCE);
  return rowPrimary?.candidate ?? best ?? fallback;
}
