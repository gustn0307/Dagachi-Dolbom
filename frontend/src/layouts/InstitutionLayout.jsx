import { Outlet } from "react-router-dom";

import InstitutionHeader from "../components/institution/InstitutionHeader";
import InstitutionSidebar from "../components/institution/InstitutionSidebar";
import "../styles/institution.css";

function InstitutionLayout() {
  return (
    <div className="institution-layout">
      <InstitutionSidebar />

      <div className="institution-main">
        <InstitutionHeader />

        <main className="institution-content">
          <Outlet />
        </main>
      </div>
    </div>
  );
}

export default InstitutionLayout;
