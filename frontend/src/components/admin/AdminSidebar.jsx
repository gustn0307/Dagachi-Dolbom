import { NavLink } from "react-router-dom";

function AdminSidebar() {
  return (
    <aside className="admin-sidebar">
      <div className="admin-logo">
        <span>◆</span>
        <div>
          <h2>다같이 돌봄</h2>
          <p>SERVICE ADMIN</p>
        </div>
      </div>
      <nav>
        <NavLink to="/admin/notices">
          <i>▤</i>
          <span>공지 관리</span>
        </NavLink>
      </nav>
      <div className="admin-system">
        <i></i>
        <p>
          <strong>시스템 정상</strong>마지막 확인 방금 전
        </p>
      </div>
    </aside>
  );
}
export default AdminSidebar;
