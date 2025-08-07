import { Injectable } from '@angular/core';

export type NotificationType = 'success' | 'info' | 'warning' | 'error';

export interface NotificationOptions {
  duration?: number;
  showAlert?: boolean;
}

@Injectable({
  providedIn: 'root'
})
export class SimpleNotificationService {
  
  constructor() {}

  /**
   * Affiche un message de succès
   */
  showSuccess(message: string, title: string = 'Succès', options: NotificationOptions = {}): void {
    this.showNotification('success', title, message, options);
  }

  /**
   * Affiche un message d'information
   */
  showInfo(message: string, title: string = 'Information', options: NotificationOptions = {}): void {
    this.showNotification('info', title, message, options);
  }

  /**
   * Affiche un message d'avertissement
   */
  showWarning(message: string, title: string = 'Avertissement', options: NotificationOptions = {}): void {
    this.showNotification('warning', title, message, options);
  }

  /**
   * Affiche un message d'erreur
   */
  showError(message: string, title: string = 'Erreur', options: NotificationOptions = {}): void {
    this.showNotification('error', title, message, options);
  }

  /**
   * Méthode générique pour afficher une notification
   */
  private showNotification(
    type: NotificationType, 
    title: string, 
    message: string, 
    options: NotificationOptions = {}
  ): void {
    const defaultOptions: NotificationOptions = {
      duration: 4000,
      showAlert: false
    };

    const finalOptions = { ...defaultOptions, ...options };

    // Option 1: Afficher une alerte native (toujours visible)
    if (finalOptions.showAlert) {
      alert(`${title}: ${message}`);
      return;
    }

    // Option 2: Créer une notification dans le DOM
    this.createDOMNotification(type, title, message, finalOptions.duration!);
  }

  /**
   * Crée une notification dans le DOM
   */
  private createDOMNotification(type: NotificationType, title: string, message: string, duration: number): void {
    // Créer l'élément de notification
    const notification = document.createElement('div');
    notification.className = `simple-notification simple-notification-${type}`;
    notification.style.cssText = `
      position: fixed;
      top: 20px;
      right: 20px;
      z-index: 10000;
      padding: 15px 20px;
      border-radius: 8px;
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
      max-width: 400px;
      min-width: 300px;
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
      font-size: 14px;
      line-height: 1.4;
      transform: translateX(100%);
      transition: transform 0.3s ease-out;
      cursor: pointer;
    `;

    // Couleurs selon le type
    const colors = {
      success: { bg: '#d4edda', border: '#c3e6cb', text: '#155724', icon: '✓' },
      info: { bg: '#d1ecf1', border: '#bee5eb', text: '#0c5460', icon: 'ℹ' },
      warning: { bg: '#fff3cd', border: '#ffeaa7', text: '#856404', icon: '⚠' },
      error: { bg: '#f8d7da', border: '#f5c6cb', text: '#721c24', icon: '✗' }
    };

    const color = colors[type];
    notification.style.backgroundColor = color.bg;
    notification.style.border = `1px solid ${color.border}`;
    notification.style.color = color.text;

    // Contenu de la notification
    notification.innerHTML = `
      <div style="display: flex; align-items: flex-start; gap: 10px;">
        <div style="font-size: 18px; font-weight: bold; flex-shrink: 0;">${color.icon}</div>
        <div style="flex: 1;">
          <div style="font-weight: bold; margin-bottom: 4px;">${title}</div>
          <div>${message}</div>
        </div>
        <button onclick="this.parentElement.parentElement.remove()" style="
          background: none;
          border: none;
          font-size: 18px;
          cursor: pointer;
          color: inherit;
          opacity: 0.7;
          padding: 0;
          margin-left: 10px;
        ">×</button>
      </div>
    `;

    // Ajouter au DOM
    document.body.appendChild(notification);

    // Animation d'entrée
    setTimeout(() => {
      notification.style.transform = 'translateX(0)';
    }, 10);

    // Auto-suppression après la durée spécifiée
    setTimeout(() => {
      notification.style.transform = 'translateX(100%)';
      setTimeout(() => {
        if (notification.parentElement) {
          notification.remove();
        }
      }, 300);
    }, duration);

    // Suppression au clic
    notification.addEventListener('click', () => {
      notification.style.transform = 'translateX(100%)';
      setTimeout(() => {
        if (notification.parentElement) {
          notification.remove();
        }
      }, 300);
    });
  }

  /**
   * Efface toutes les notifications
   */
  clear(): void {
    const notifications = document.querySelectorAll('.simple-notification');
    notifications.forEach(notification => {
      (notification as HTMLElement).style.transform = 'translateX(100%)';
      setTimeout(() => {
        if (notification.parentElement) {
          notification.remove();
        }
      }, 300);
    });
  }
}
