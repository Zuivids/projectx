import { Link } from 'react-router'

export function NotFoundPage() {
  return (
    <div className="narrow">
      <h1>Lapa nav atrasta</h1>
      <p>
        <Link to="/">Uz sākumu</Link>
      </p>
    </div>
  )
}
