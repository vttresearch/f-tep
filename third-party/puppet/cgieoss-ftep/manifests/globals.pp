# Class for setting cross-class global overrides.
class ftep::globals (
  $manage_package_repo       = true,

  # Base URL for ftep::proxy
  $base_url                  = "http://${facts['fqdn']}",

  # Hostnames and IPs for components
  $db_hostname               = 'ftep-db',
  $drupal_hostname           = 'ftep-drupal',
  $geoserver_hostname        = 'ftep-geoserver',
  $proxy_hostname            = 'ftep-proxy',
  $webapp_hostname           = 'ftep-webapp',
  $wps_hostname              = 'ftep-wps',
  $server_hostname           = 'ftep-server',
  $monitor_hostname          = 'ftep-monitor',
  $resto_hostname            = 'ftep-resto',

  $hosts_override            = { },

  # All classes should share this database config, or override it if necessary
  $ftep_db_name              = 'ftep',
  $ftep_db_v2_name           = 'ftep_v2',
  $ftep_db_username          = 'ftep-user',
  $ftep_db_password          = 'ftep-pass',
  $ftep_db_resto_name        = 'ftep_resto',
  $ftep_db_resto_username    = 'ftep-resto',
  $ftep_db_resto_password    = 'ftep-resto-pass',
  $ftep_db_resto_su_username = 'ftep-resto-admin',
  $ftep_db_resto_su_password = 'ftep-resto-admin-pass',

  # App server port config for HTTP and gRPC
  $server_application_port   = 8090,
  $worker_application_port   = 8091,
  $server_grpc_port          = 6565,
  $worker_grpc_port          = 6566,

  # Geoserver port config
  $geoserver_port            = 9080,
  $geoserver_stopport        = 9079,

  # monitor port config
  $grafana_port              = 8089,
  $monitor_data_port         = 8086,

  # graylog config
  $graylog_secret            = 'bQ999ugSIvHXfWQqrwvAomNxaMsErX6I4UWicpS9ZU8EDmuFnhX9AmcoM43s4VGKixd2f6d6cELbRuPWO5uayHnBrBbNWVth',
  $graylog_sha256            = 'a7fdfe53e2a13cb602def10146388c65051c67e60ee55c051668a1c709449111', # sha256 of graylogpass
  $graylog_port              = 8087,
  $graylog_context_path      = '/log',
  $graylog_gelf_tcp_port     = 5140,

) {

  # Alias reverse-proxy hosts via hosts file
  ensure_resources(host, $hosts_override)

  # Setup of the repo only makes sense globally, so we are doing this here.
  if($manage_package_repo) {
    require ::ftep::repo
  }
}
