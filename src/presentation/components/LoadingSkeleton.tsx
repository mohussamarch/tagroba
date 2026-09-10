/** Reserves the first cards' space while data is unavailable; no fabricated amounts. */
export function LoadingSkeleton({label='بنحمّل البيانات…'}:{label?:string}) {
  return <div className="loadingSkeleton" role="status" aria-label={label}>
    <span className="visually-hidden">{label}</span>
    <div className="loadingSkeleton__line" aria-hidden="true"/>
    <div className="loadingSkeleton__hero" aria-hidden="true"/>
    <div className="loadingSkeleton__metrics" aria-hidden="true"><i/><i/><i/></div>
    <div className="loadingSkeleton__hero" aria-hidden="true"/>
  </div>
}
