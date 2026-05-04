import type { Preview, Decorator } from '@storybook/react'
import React from 'react'
import '../src/tokens/tokens.css'

/**
 * `withTheme` decorator stub: applies `data-theme` to a wrapper div based on
 * the global `theme` toolbar value. Phase 1 only ships a minimal dark stub —
 * Phase 2 will extend it with full palette swap.
 */
const withTheme: Decorator = (Story, context) => {
  const theme = (context.globals['theme'] as string) ?? 'light'
  return React.createElement(
    'div',
    {
      'data-theme': theme,
      style: {
        background: 'var(--color-bg)',
        color: 'var(--color-text)',
        fontFamily: 'var(--font-family-sans)',
        fontSize: 'var(--font-size-base)',
        padding: 24,
        minHeight: '100vh',
        boxSizing: 'border-box',
      },
    },
    React.createElement(Story, null),
  )
}

const preview: Preview = {
  decorators: [withTheme],
  globalTypes: {
    theme: {
      name: 'Theme',
      description: 'Light/Dark theme switch',
      defaultValue: 'light',
      toolbar: {
        icon: 'mirror',
        items: [
          { value: 'light', title: 'Light' },
          { value: 'dark', title: 'Dark' },
        ],
        dynamicTitle: true,
      },
    },
  },
  parameters: {
    backgrounds: {
      default: 'app',
      values: [
        { name: 'app', value: '#F7F8FA' },
        { name: 'surface', value: '#FFFFFF' },
        { name: 'dark', value: '#0F1216' },
      ],
    },
    controls: {
      matchers: {
        color: /(background|color)$/i,
        date: /Date$/i,
      },
    },
    layout: 'padded',
  },
}

export default preview
