export function useCharCount(text: string, max = 280) {
  const remaining = max - text.length;
  return { remaining, isOver: remaining < 0 };
}
