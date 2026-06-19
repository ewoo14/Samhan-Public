import { MascotLoader } from './MascotLoader'

const defaultLoader = <MascotLoader />
const labeledLoader = <MascotLoader label="거래처 목록 불러오는 중" size="lg" />

// @ts-expect-error MascotLoader size 는 sm/md/lg 만 허용한다.
const invalidLoader = <MascotLoader size="xl" />

export { defaultLoader, labeledLoader, invalidLoader }
