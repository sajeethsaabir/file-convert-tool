%global __jar_repack 0
%global __brp_mangle_shebangs 0

Name: convertor-tool
Version: 1.0.0
Release: 1%{?dist}
Summary: Bi-directional document converter

License: Apache 2.0
Source0: %{name}-%{version}.tar.gz
BuildArch: noarch

Requires: java-headless >= 21

%description
Bi-directional document converter. Converts between DOCX, PDF, and
Markdown formats. Supports: DOCX->MD, PDF->MD, MD->DOCX, MD->PDF.
Includes both GUI and CLI interfaces.

%prep
%setup -q

%install
install -D -m 755 usr/bin/convertor-tool %{buildroot}%{_bindir}/convertor-tool
install -D -m 644 usr/share/%{name}/%{name}.jar %{buildroot}%{_datadir}/%{name}/%{name}.jar
install -D -m 644 usr/share/applications/convertor-tool.desktop %{buildroot}%{_datadir}/applications/convertor-tool.desktop
install -D -m 644 usr/share/icons/hicolor/scalable/apps/convertor-tool.svg %{buildroot}%{_datadir}/icons/hicolor/scalable/apps/convertor-tool.svg
install -D -m 644 usr/share/man/man1/convertor-tool.1.gz %{buildroot}%{_mandir}/man1/convertor-tool.1.gz
install -D -m 644 usr/share/doc/%{name}/copyright %{buildroot}%{_docdir}/%{name}/copyright

%post
gtk-update-icon-cache -q -t %{_datadir}/icons/hicolor 2>/dev/null || :
update-desktop-database -q 2>/dev/null || :

%postun
gtk-update-icon-cache -q -t %{_datadir}/icons/hicolor 2>/dev/null || :
update-desktop-database -q 2>/dev/null || :

%files
%defattr(-,root,root,-)
%{_bindir}/convertor-tool
%{_datadir}/%{name}/
%{_datadir}/applications/convertor-tool.desktop
%{_datadir}/icons/hicolor/scalable/apps/convertor-tool.svg
%{_mandir}/man1/convertor-tool.1.gz
%doc %{_docdir}/%{name}/copyright

%changelog
* Sun Jul 26 2026 Convertor Tool Team <convertor-tool@example.com> - 1.0.0-1
- Initial RPM package
