export type TvDirection = "up" | "down" | "left" | "right";

export interface FocusGeometry {
  left: number;
  top: number;
  right: number;
  bottom: number;
}

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

    const edgeDistance = Math.max(0, primary);
    const score = edgeDistance + secondary * 1.8 + (overlaps ? 0 : 120);
    if (score < bestScore) {
      best = candidate;
      bestScore = score;
    }
  }

  return best;
}
