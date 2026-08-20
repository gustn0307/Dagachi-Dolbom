function PageHeader({ eyebrow, title, text }) {
  return (
    <section className="page-heading">
      <p>{eyebrow}</p>
      <h1>{title}</h1>
      <span>{text}</span>
    </section>
  );
}

export default PageHeader;