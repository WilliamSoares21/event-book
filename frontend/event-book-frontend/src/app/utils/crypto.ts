export async function calculateSha256Base64(file: File): Promise<string> {
  const arrayBuffer = await file.arrayBuffer();
  const hashBuffer = await crypto.subtle.digest('SHA-256', arrayBuffer);
  const hashArray = Array.from(new Uint8Array(hashBuffer));
  const binaryString = hashArray.map(b => String.fromCharCode(b)).join('');
  return btoa(binaryString);
}
