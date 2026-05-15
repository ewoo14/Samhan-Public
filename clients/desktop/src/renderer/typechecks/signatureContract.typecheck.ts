import type {
  RecordDriverSignatureResponse,
  RecordSignatureResponse,
  SignatureShareView,
} from '../api/signature'

declare const driverResponse: RecordDriverSignatureResponse
declare const recipientResponse: RecordSignatureResponse
declare const shareView: SignatureShareView

// @ts-expect-error driver-facing 공개 응답에 내부 signatureId 가 포함되면 안 된다.
const driverSignatureId = driverResponse.signatureId
// @ts-expect-error driver-facing 공개 응답에 raw downloadUrl 이 포함되면 안 된다.
const driverDownloadUrl = driverResponse.downloadUrl
// @ts-expect-error driver-facing 공개 응답에 storageKey 가 포함되면 안 된다.
const driverStorageKey = driverResponse.storageKey

// @ts-expect-error 인수자 공개 응답에 내부 signatureId 가 포함되면 안 된다.
const recipientSignatureId = recipientResponse.signatureId
// @ts-expect-error 인수자 공개 응답에 raw downloadUrl 이 포함되면 안 된다.
const recipientDownloadUrl = recipientResponse.downloadUrl
// @ts-expect-error 인수자 공개 응답에 storageKey 가 포함되면 안 된다.
const recipientStorageKey = recipientResponse.storageKey

// @ts-expect-error share view 에 slip UUID 가 포함되면 안 된다.
const shareSlipId = shareView.slip.id
// @ts-expect-error share view 에 signature UUID 가 포함되면 안 된다.
const shareSignatureId = shareView.signature.signatureId
// @ts-expect-error share view 에 raw downloadUrl 이 포함되면 안 된다.
const shareDownloadUrl = shareView.signature.downloadUrl
// @ts-expect-error share view 에 storageKey 가 포함되면 안 된다.
const shareStorageKey = shareView.signature.storageKey

void [
  driverSignatureId,
  driverDownloadUrl,
  driverStorageKey,
  recipientSignatureId,
  recipientDownloadUrl,
  recipientStorageKey,
  shareSlipId,
  shareSignatureId,
  shareDownloadUrl,
  shareStorageKey,
]

export {}
