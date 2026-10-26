export default defineNuxtRouteMiddleware((to) => {
  if (to.path.includes('/admin/') && !useOwnerAuth().value.authenticated) return navigateTo('/login')
})
