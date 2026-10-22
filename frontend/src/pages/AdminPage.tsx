/**
 * Admin panel skeleton. Only reachable for admins who passed MFA in this session; the
 * server enforces the same rule on every /api/v1/admin request.
 */
export function AdminPage() {
  return (
    <>
      <h1>Administrēšana</h1>
      <div className="features">
        <div className="card">
          <h2>Lietotāji</h2>
          <p className="muted">Meklēt, bloķēt, atiestatīt MFA.</p>
          <span className="badge">Drīzumā</span>
        </div>
        <div className="card">
          <h2>Kursi</h2>
          <p className="muted">Kursi, sadaļas un nodarbības.</p>
          <span className="badge">Drīzumā</span>
        </div>
        <div className="card">
          <h2>Pasūtījumi</h2>
          <p className="muted">Maksājumi un atmaksas.</p>
          <span className="badge">Drīzumā</span>
        </div>
      </div>
    </>
  )
}
