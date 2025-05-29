def has_content: . and (. | length > 0);

.[0] as $community_list | .[1] as $official_list |

[
  # Extract AAGUIDs from community-driven list
  ($community_list | to_entries | map(
    select(.value.name | has_content) | 
    {(.key): .value.name}
  )),
  
  # Extract AAGUIDs from official FIDO MDS (overwrites community data)
  ($official_list.entries | map(
    select(.aaguid | has_content) |
    select(.metadataStatement.description | has_content) |
    {(.aaguid): .metadataStatement.description}
  ))
] | flatten | add // {}
