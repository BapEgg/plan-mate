import './App.css'

function App() {
  return (
    <main className="app-shell">
      <section className="intro-panel" aria-labelledby="page-title">
        <p className="eyebrow">PlanMate</p>
        <h1 id="page-title">Collaborative trip planning starts here.</h1>
        <p className="description">
          This frontend module is ready for the first PlanMate user flows:
          authentication, trip creation, accommodations, itinerary generation,
          and collaboration.
        </p>
      </section>

      <section className="status-grid" aria-label="Frontend setup status">
        <article>
          <span>01</span>
          <h2>React</h2>
          <p>Component-based UI foundation.</p>
        </article>
        <article>
          <span>02</span>
          <h2>TypeScript</h2>
          <p>Typed frontend implementation.</p>
        </article>
        <article>
          <span>03</span>
          <h2>Vite</h2>
          <p>Fast local development and production build.</p>
        </article>
      </section>
    </main>
  )
}

export default App
