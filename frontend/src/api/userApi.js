import api, { unwrapData } from "./api";

export const getActivities = (params = {}) =>
  unwrapData(api.get("/api/activities", { params }));

export const getActivity = (activityId) =>
  unwrapData(api.get(`/api/activities/${activityId}`));

export const userApi = {
  getActivities,
  getActivity,
};
