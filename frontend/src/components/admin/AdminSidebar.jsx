import { NavLink } from "react-router-dom";

function AdminSidebar(){return <aside className="admin-sidebar"><div className="admin-logo"><span>◆</span><div><h2>이웃을 잇다</h2><p>SERVICE ADMIN</p></div></div><nav><NavLink to="/admin/users"><i>♙</i><span>일반 사용자</span></NavLink><NavLink to="/admin/institutions"><i>▣</i><span>기관 관리</span><em>3</em></NavLink></nav><div className="admin-system"><i></i><p><strong>시스템 정상</strong>마지막 확인 방금 전</p></div></aside>}
export default AdminSidebar;
