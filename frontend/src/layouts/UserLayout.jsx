import { Outlet } from "react-router-dom";

import UserHeader from "../components/navigation/UserHeader";
import UserFooter from "../components/navigation/UserFooter";

function UserLayout() {
  return (
    <div className="app-shell">
      <UserHeader />

      <main>
        <Outlet />
      </main>

      <UserFooter />
    </div>
  );
}

export default UserLayout;