import { Routes } from '@angular/router';
import { MainLayoutComponent } from './shared/layout/main-layout.component';
import { DashboardComponent } from './features/dashboard/dashboard.component';
import { DataProfilerComponent } from './features/data-profiler/data-profiler.component';
import { DataPreparationComponent } from './features/data-preparation/data-preparation.component';
import { SignupComponent } from './features/auth/signup/signup.component';
import { LoginComponent } from './features/auth/login/login.component';
import { UnauthorizedComponent } from './shared/components/unauthorized/unauthorized.component';
import { authGuard } from './core/guards/auth.guard';
import { viewDataGuard, editDataGuard, uploadDataGuard, exportDataGuard, adminGuard } from './core/guards/role.guard';


import { publicGuard } from './core/guards/public.guard';
import ProfileComponent from "./features/profile/profile.component"
import { PreparationDetailComponent } from './features/data-preparation/preparation-detail/preparation-detail.component';
import { DatasetViewComponent } from './features/datasets/dataset-view/dataset-view.component';
import { DatasetsListComponent } from './features/datasets/datasets-list/datasets-list.component';


export const routes: Routes = [
  // Public routes (without layout/sidebar)
  {
    path: '',
    children: [
      { 
        path: 'login', 
        component: LoginComponent, 
        canActivate: [publicGuard] 
      },
      { 
        path: 'signup', 
        component: SignupComponent, 
        canActivate: [publicGuard] 
      },

      { path: 'unauthorized', component: UnauthorizedComponent },
      { path: '', redirectTo: 'login', pathMatch: 'full' } // Default redirect to login
    ]
  },

  // Protected routes (with layout/sidebar)
  {
    path: '',
    component: MainLayoutComponent,
    canActivate: [authGuard],
    children: [
      { path: 'dashboard', component: DashboardComponent },
      { 
        path: 'data-profiler', 
        component: DataProfilerComponent,
        canActivate: [viewDataGuard]
      },

      { 
        path: 'data-profiler/:id', 
        component: DataProfilerComponent,
        canActivate: [viewDataGuard]
      },
      { path: 'profile', component: ProfileComponent },
      { 
        path: 'data-preparation', 
        component: DataPreparationComponent,
        canActivate: [editDataGuard]
      },
      { 
        path: 'data-preparation/:id', 
        component: PreparationDetailComponent,
        canActivate: [editDataGuard]
      },
      { 
        path: 'datasets', 
        component: DatasetsListComponent,
        canActivate: [viewDataGuard]
      },
      { 
        path: 'datasets/:id', 
        component: DatasetViewComponent,
        canActivate: [viewDataGuard]
      },
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' }
    ]
  },

  {
    path: 'metrics',
    loadComponent: () => import('./core/components/metrics-dashboard/metrics-dashboard.component').then(c => c.MetricsDashboardComponent),
    canActivate: [adminGuard]
  },

  // Handle unknown routes
  { path: '**', redirectTo: 'login' }
];