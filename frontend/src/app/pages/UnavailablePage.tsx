import { Heading } from '../../shared/components/PageElements';
export function UnavailablePage({ title }: { title: string }) {
  return (
    <>
      <Heading
        title={title}
        subtitle="This desk is not connected to a FreightFlow service yet."
      />
      <div className="card p-6">
        <p>
          Open a connected workspace from the sidebar to continue working with
          live data.
        </p>
      </div>
    </>
  );
}
