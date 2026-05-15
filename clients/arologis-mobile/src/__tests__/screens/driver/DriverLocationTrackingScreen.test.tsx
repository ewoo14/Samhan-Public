import React from 'react';
import { Alert, Switch } from 'react-native';
import { fireEvent, render, waitFor } from '@testing-library/react-native';

jest.mock('react-native-safe-area-context', () => {
  const ReactActual = jest.requireActual('react');
  const RN = jest.requireActual('react-native');

  return {
    SafeAreaView: ({ children }: { children: React.ReactNode }) => ReactActual.createElement(RN.View, null, children),
    SafeAreaProvider: ({ children }: { children: React.ReactNode }) => ReactActual.createElement(RN.View, null, children),
  };
});

jest.mock('../../../api/arologis', () => ({
  reportLocation: jest.fn(),
}));

jest.mock('../../../hooks/useGpsPermission', () => ({
  getCurrentPositionAsync: jest.fn().mockResolvedValue({
    latitude: 37.1234567,
    longitude: 127.7654321,
    capturedAt: '2026-05-16T08:00:00',
  }),
}));

import { reportLocation } from '../../../api/arologis';
import DriverLocationTrackingScreen from '../../../screens/driver/DriverLocationTrackingScreen';

const leakedUuid = '11111111-2222-3333-4444-555555555555';
const leakedDownloadUrl = 'https://storage.example/private/location.json';
const leakedStorageKey = 'locations/internal-key.json';

function textContent(node: unknown): string {
  if (typeof node === 'string') return node;
  if (Array.isArray(node)) return node.map(textContent).join('');
  if (node && typeof node === 'object' && 'props' in node) {
    return textContent((node as { props: { children?: unknown } }).props.children);
  }
  return '';
}

describe('DriverLocationTrackingScreen UUID-free guard', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    jest.spyOn(Alert, 'alert').mockImplementation((_title, _message, buttons) => {
      buttons?.find((button) => button.text === '시작')?.onPress?.();
    });
    (reportLocation as jest.Mock).mockResolvedValue({
      capturedAt: '2026-05-16T08:00:00',
      locationId: leakedUuid,
      downloadUrl: leakedDownloadUrl,
      storageKey: leakedStorageKey,
    });
  });

  afterEach(() => {
    jest.restoreAllMocks();
  });

  it('GPS 보고 성공 화면에 locationId/downloadUrl/storageKey를 표시하지 않는다', async () => {
    const utils = render(<DriverLocationTrackingScreen token="jwt-x" backgroundGranted={false} />);

    fireEvent(utils.UNSAFE_getByType(Switch), 'valueChange', true);

    await waitFor(() => expect(reportLocation).toHaveBeenCalledTimes(1));
    expect(utils.getByText('성공')).toBeTruthy();

    const renderedText = textContent(utils.toJSON());
    expect(renderedText).not.toContain('locationId');
    expect(renderedText).not.toContain('downloadUrl');
    expect(renderedText).not.toContain('storageKey');
    expect(renderedText).not.toContain(leakedUuid);
    expect(renderedText).not.toContain(leakedDownloadUrl);
    expect(renderedText).not.toContain(leakedStorageKey);

    utils.unmount();
  });
});
