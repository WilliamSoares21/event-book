export interface EventImageUploadRequest {
  contentType: string;
  sha256Base64: string;
  writeId: string;
  sizeBytes: number;
}

export interface EventImageUploadUrlResponse {
  uploadUrl: string;
  objectKey: string;
  publicUrl: string;
  expiresAt: string;
}

export interface EventImageConfirmRequest {
  objectKey: string;
  writeId: string;
  sha256Base64: string;
  sizeBytes: number;
}
