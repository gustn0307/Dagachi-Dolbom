import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../../auth/AuthContext";
import { getMyInstitution } from "../../api/institutionApi";

function InstitutionHeader() {
  const navigate = useNavigate();
  const { user, logout } = useAuth();

  const [institutionName, setInstitutionName] = useState("");

  useEffect(() => {
    if (!user?.institutionId) {
      return;
    }

    let ignore = false;

    const fetchInstitution = async () => {
      try {
        const institution = await getMyInstitution();
        if (!ignore) {
          setInstitutionName(institution.name);
        }
      } catch {
        if (!ignore) {
          setInstitutionName("");
        }
      }
    };

    fetchInstitution();

    return () => {
      ignore = true;
    };
  }, [user?.institutionId]);

  const handleLogout = () => {
    logout();
    navigate("/login", { replace: true });
  };

  const avatarInitial = user?.name ? user.name.charAt(0) : "?";

  return (
    <header className="institution-header">
      <div className="institution-mobile-brand">
        <span>♥</span> 다같이 돌봄
      </div>
      <div className="header-search">
        <span>⌕</span>
        <input aria-label="통합 검색" placeholder="이름, 제보 번호로 검색" />
      </div>
      <div className="institution-header-user">
        <span className="user-avatar">{avatarInitial}</span>
        <span>
          <strong>{user?.name ?? "이름"}</strong>
          <small>{institutionName || "기관명"}</small>
        </span>
        <button className="header-more" type="button" onClick={handleLogout}>
          로그아웃
        </button>
      </div>
    </header>
  );
}

export default InstitutionHeader;