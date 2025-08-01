import { ApplicationConfig, importProvidersFrom } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideAnimations } from '@angular/platform-browser/animations';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { JwtModule } from '@auth0/angular-jwt';
import { routes } from './app.routes';
import { jwtInterceptor } from './core/interceptors/jwt.interceptor';
import { metricsInterceptor } from './core/interceptors/metrics.interceptor';
import { corsFixInterceptor } from './core/interceptors/cors-fix.interceptor';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { providePrimeNG } from 'primeng/config';
import Aura from '@primeng/themes/aura';
import { definePreset } from '@primeng/themes';

// Function to get the JWT token from local storage
export function tokenGetter() {
  const token = localStorage.getItem('auth_token');
  return token;
}

const MyPreset = definePreset(Aura, {
  semantic: {
    primary: {
      50: '{sky.50}',
      100: '{sky.100}',
      200: '{sky.200}',
      300: '{sky.300}',
      400: '{sky.400}',
      500: '{sky.500}',
      600: '{sky.600}',
      700: '{sky.700}',
      800: '{sky.800}',
      900: '{sky.900}',
      950: '{sky.950}'
    },

  }
});

export const appConfig: ApplicationConfig = {
  providers: [
    provideRouter(routes),
    provideAnimations(),
    provideHttpClient(withInterceptors([jwtInterceptor, metricsInterceptor, corsFixInterceptor])),
    importProvidersFrom(
      JwtModule.forRoot({
        config: {
          tokenGetter: tokenGetter,
          allowedDomains: ['localhost:8443'],
          disallowedRoutes: [
            'api/auth/login', 
            'api/auth/register',
            'api/auth/refresh'
          ]
        }
      })

    ),
    providePrimeNG({
      theme: {
        preset: MyPreset,
        options: {
           darkModeSelector: false || 'none'
        }
      }
    })
  ]
};

