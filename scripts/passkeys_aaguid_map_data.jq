# This script is used by the passkeys_make_aaguid_datafile.sh script.

def has_content: . and (. | length > 0);

.[0] as $community_list | .[1] as $official_list |

[
  # Extract AAGUIDs from community-driven list
  ($community_list | to_entries | map(
    select(.value.name | has_content) | 
    {(.key): {
      description: .value.name,
      icon: .value.icon_light
    }}
  )),
  
  # Extract AAGUIDs from official FIDO MDS (overwrites community data)
  ($official_list.entries | map(
    select(.aaguid | has_content) |
    select(.metadataStatement.description | has_content) |
    {(.aaguid): {description: .metadataStatement.description, icon: .metadataStatement.icon}}
  ))
] | flatten | add // {}
